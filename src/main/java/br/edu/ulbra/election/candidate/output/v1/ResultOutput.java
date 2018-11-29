package br.edu.ulbra.election.candidate.output.v1;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(description = "Result Output Information")
public class ResultOutput {

    @ApiModelProperty(example = "10000", notes = "Total Votes")
    private Long totalVotes;

    public Long getTotalVotes() {
        return totalVotes;
    }

}
